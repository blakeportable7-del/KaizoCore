package com.ironmonone.pc

import java.io.File

/**
 * Where the desktop tests find the games and the team's patch: the main checkout's .vendor folder (never in the repo,
 * never copied into it), found from this checkout or the main checkout a worktree belongs to, as the app's HnsEngineTest
 * finds it. Without them a test returns early; under IRONMON_REQUIRE_DUMPS=1 (the release gate) a missing file fails.
 */
object TestFiles {
    val repo: File = File(System.getProperty("kaizocore.repo") ?: "..").absoluteFile

    private val vendors: List<File> by lazy {
        val out = ArrayList<File>()
        var d: File? = repo
        while (d != null) {
            out += File(d, ".vendor")
            val git = File(d, ".git")
            if (git.isFile) git.readText().substringAfter("gitdir:").trim().substringBefore("/.git/").let { out += File(it, ".vendor") }
            d = d.parentFile
        }
        out
    }

    private fun find(env: String?, rel: String): File? {
        env?.let { System.getenv(it) }?.let { File(it) }?.takeIf { it.isFile }?.let { return it }
        val f = vendors.map { File(it, rel) }.firstOrNull { it.isFile }
        if (f == null && System.getenv("IRONMON_REQUIRE_DUMPS") == "1") throw AssertionError("IRONMON_REQUIRE_DUMPS: .vendor/$rel is missing")
        return f
    }

    /** KaizoCore PC's comfort build (the client's easter eggs, patch 0061), by its CRC: hns-kaizo-F4E28E3D.gba, else a
     *  hns-kaizo.gba of that CRC. The app's build and older ones lie in the same folders. */
    val comfortBuild: File? by lazy {
        val want = com.ironmonone.core.RomKind.HNS_PC_BUILD_CRC
        System.getenv("IRONMON_HNS_PC_ROM")?.let { File(it) }?.takeIf { it.isFile }?.let { return@lazy it }
        val f = vendors.flatMap { listOf(File(it, "hns/hns-kaizo-%08X.gba".format(want)), File(it, "hns/hns-kaizo.gba")) }
            .firstOrNull { it.isFile && java.util.zip.CRC32().apply { update(it.readBytes()) }.value == want }
        if (f == null && System.getenv("IRONMON_REQUIRE_DUMPS") == "1") throw AssertionError("IRONMON_REQUIRE_DUMPS: .vendor/hns/hns-kaizo-%08X.gba is missing".format(want))
        f
    }
    /** The Android app's comfort build (no easter eggs, patch 0061), by its CRC: hns-kaizo-F5F37A8E.gba. */
    val appBuild: File? by lazy {
        val want = com.ironmonone.core.RomKind.HEARTSOUL_KAIZO_206.expectedCrc
        vendors.map { File(it, "hns/hns-kaizo-%08X.gba".format(want)) }
            .firstOrNull { it.isFile && java.util.zip.CRC32().apply { update(it.readBytes()) }.value == want }
            ?: run { if (System.getenv("IRONMON_REQUIRE_DUMPS") == "1") throw AssertionError("IRONMON_REQUIRE_DUMPS: .vendor/hns/hns-kaizo-%08X.gba is missing".format(want)); null }
    }
    /** The official Heart & Soul 2.0.6, CRC 01713508. */
    val official206: File? by lazy { find(null, "roms/hns-206.gba") }
    /** Pokemon Emerald (USA), CRC 1F1C08FB. */
    val emerald: File? by lazy { find(null, "roms/emerald-u.gba") }
    /** The Heart & Soul team's 2.0.6 patch (theirs, never shipped). */
    val teamPatch: File? by lazy { find(null, "hns/pokemonHnS_v2.0.6.ups") }

    fun tempDir(name: String): File = File(System.getProperty("java.io.tmpdir"), "kcpc-test-$name-${System.nanoTime()}").apply { mkdirs() }
}
