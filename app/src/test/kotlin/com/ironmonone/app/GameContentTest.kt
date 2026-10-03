package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc34 (2026-10-03): what a game load hands the core, and for how long.
 *
 * Black 2 and White 2 held their 512 MB ROM twice: LibretroDroid read the file into the heap and kept it until the core
 * was torn down, beside the copy melonDS makes for itself (1.07 GB of native heap with Black 2 at its title screen, two
 * 512 MB blocks). And the retro_game_info the core was handed was a local of the loader, while the classic melonDS core
 * keeps that pointer and reads the ROM through it again on retro_reset, so a DS Restart read a dead stack frame and
 * crashed (SIGSEGV at 0xa in the first frame after it, on the emulator with rc34).
 *
 * Now the struct, its path and the bytes live as long as the game, and the bytes are a read-only mapping of the file
 * whose pages leave the process once the core has its copy (cpp/gamecontent.h). No JVM test can call into the native
 * library, so the C++ is pinned in its source, as NativeGlueTest does; the emulator measured it (Black 2 at its title
 * screen: 1285 MB before, the docs carry the figure after).
 */
class GameContentTest {
    private fun src(rel: String) = File("../libretrodroid/src/main/cpp/$rel").readText().replace("\r\n", "\n")
    private val core = src("libretrodroid.cpp")
    private val header = src("libretrodroid.h")
    private val content = src("gamecontent.cpp")
    private val jni = src("libretrodroidjni.cpp")

    /** The body of a C++ function from its signature to the closing brace at the start of a line. */
    private fun body(text: String, signature: String): String {
        val start = text.indexOf(signature)
        assertTrue(start >= 0, "no $signature")
        return text.substring(start, text.indexOf("\n}\n", start))
    }

    private val loaders = listOf(
        "void LibretroDroid::loadGameFromPath(",
        "void LibretroDroid::loadGameFromBytes(",
        "void LibretroDroid::loadGameFromVirtualFiles(",
    )

    @Test
    fun `the core is handed a retro_game_info that lives as long as the game`() {
        assertTrue("struct retro_game_info contentInfo {};" in header, "a member, not a local")
        val hand = body(core, "bool LibretroDroid::handOver(")
        assertTrue("core->retro_load_game(&contentInfo)" in hand)
        assertTrue(hand.indexOf("contentInfo.data = content.data();") in 0 until hand.indexOf("core->retro_load_game(&contentInfo)"))
        assertTrue("contentInfo.path = contentPath.empty() ? nullptr : contentPath.c_str();" in hand, "the path lives in the object too")
        assertEquals(1, core.split("core->retro_load_game(").size - 1, "every load goes through handOver")
        for (l in loaders) {
            val b = body(core, l)
            assertFalse("retro_game_info game_info" in b, "$l keeps no retro_game_info of its own")
            assertTrue("handOver(" in b, "$l hands over through handOver")
        }
    }

    @Test
    fun `the ROM is mapped, not read into the heap, and its pages leave once the core has its copy`() {
        val hand = body(core, "bool LibretroDroid::handOver(")
        val load = hand.indexOf("core->retro_load_game(&contentInfo)")
        assertTrue(hand.indexOf("content.dropResidentPages();") > load, "dropped after the core copied the game")
        for (l in loaders) assertFalse("readFileAsBytes" in body(core, l), "$l reads no copy into the heap")
        assertTrue("GameContent::fromPath(gamePath)" in body(core, "void LibretroDroid::loadGameFromPath("))
        assertTrue("GameContent::fromFd(firstFileFD)" in body(core, "void LibretroDroid::loadGameFromVirtualFiles("))

        val from = body(content, "GameContent GameContent::fromFd(int fd) {")
        assertTrue("mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0)" in from, "read-only and private: page cache, not heap")
        assertTrue("Closer" in from, "the descriptor is closed however it ends")
        val drop = body(content, "void GameContent::dropResidentPages() {")
        assertTrue("madvise(mapping, mappedLength, MADV_DONTNEED)" in drop, "the pages go back; the pointer stays valid")
        assertFalse("munmap" in drop, "the mapping stays: melonDS reads the ROM through it again on a reset")

        // melonDS's reset loads the ROM again through the pointer it kept; its pages leave again after.
        val reset = body(core, "void LibretroDroid::reset() {")
        assertTrue(reset.indexOf("content.dropResidentPages();") > reset.indexOf("core->retro_reset();"))
    }

    @Test
    fun `unload frees what load took`() {
        val destroy = body(core, "void LibretroDroid::destroy() {")
        val deinit = destroy.indexOf("core->retro_deinit();")
        assertTrue(deinit >= 0)
        for (line in listOf("content.release();", "contentInfo = {};", "std::string().swap(contentPath);")) {
            assertTrue(destroy.indexOf(line) > deinit, "$line, once the core is done with it")
        }
        val release = body(content, "void GameContent::release() {")
        assertTrue("munmap(mapping, mappedLength);" in release)
        assertTrue("std::vector<char>().swap(heap);" in release, "the heap copy's capacity goes too")
        assertTrue("release();" in body(content, "GameContent::~GameContent() {"))

        // The two copies that were never freed at all: the path, a new[] per load, and the JNI's copy of a byte array.
        for (l in loaders) assertFalse("cloneToCString" in body(core, l), "$l")
        val bytes = body(jni, "Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromBytes(")
        assertFalse("new int8_t[size]" in bytes)
        assertTrue("loadGameFromBytes(std::move(bytes))" in bytes, "handed over, and freed by destroy()")
        assertTrue("gamecontent.cpp" in src("CMakeLists.txt"))
    }
}
