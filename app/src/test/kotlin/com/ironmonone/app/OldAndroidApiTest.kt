package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The app runs on Android 8 and up, but the unit tests run on a desktop JVM, which has every Java API: a call that
 * Android only gained in 13 compiles, passes here and throws NoSuchMethodError on a phone. That is how every
 * file-to-file patch broke on Android 8 to 12 (InputStream.readNBytes, rc33 audit P1). This keeps those calls out of
 * the code that ships, in every module.
 */
class OldAndroidApiTest {
    private val modules = listOf("app", "core-api", "core-patch", "core-recipe", "tracker-gba", "tracker-nds", "editor", "engine-zx", "engine-natdex", "engine-maxdex")
    private val banned = listOf(".readNBytes(", ".readAllBytes(", ".transferTo(")

    @Test
    fun `no Java 9 to 11 stream calls in shipped code`() {
        val hits = mutableListOf<String>()
        for (m in modules) {
            val root = File("../$m/src/main")
            if (!root.isDirectory) continue
            root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.forEach { f ->
                f.readLines().forEachIndexed { i, line ->
                    val code = line.trim()
                    if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                    for (b in banned) if (b in code) hits += "${f.path}:${i + 1}: $code"
                }
            }
        }
        assertTrue(hits.isEmpty(), "Android 13+ only; use Patcher.head or a read loop:\n" + hits.joinToString("\n"))
    }

    /**
     * rc32 audit P3 #35: PackageInfo.longVersionCode is API 28. Called bare, it threw on Android 8.0 and 8.1, and the
     * runCatching around it made the crash report's version and the next run's app stamp "unknown" on every build there,
     * so a run made ahead by the old build was taken after an update. It is read in one place, behind the SDK check.
     */
    @Test
    fun `the version code is read in one place, behind the Android 9 check`() {
        val hits = mutableListOf<String>()
        for (m in modules) {
            val root = File("../$m/src/main")
            if (!root.isDirectory) continue
            root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.forEach { f ->
                f.readLines().forEachIndexed { i, line ->
                    val code = line.trim()
                    if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                    if (".longVersionCode" in code) hits += "${f.path}:${i + 1}: $code"
                }
            }
        }
        assertEquals(1, hits.size, "one guarded reader (UpdateCheck.versionCode):\n" + hits.joinToString("\n"))
        assertTrue("Build.VERSION.SDK_INT >= Build.VERSION_CODES.P" in hits.single() && "versionCode.toLong()" in hits.single(), hits.single())
        val src = File("src/main/kotlin/com/ironmonone/app")
        assertTrue("UpdateCheck.versionCode(p)" in File(src, "NextRunJob.kt").readText(), "the next run's app stamp")
        assertTrue("UpdateCheck.versionCode(p)" in File(src, "CrashLog.kt").readText(), "the crash report's version")
    }

    /**
     * rc33 audit P1: the README had lost the Nat. Dex Extension link and DrMaple's credit, which Cyan's written grant
     * and NOTICE require ("Credit me (CyanSixFour / CyanSMP64) and link ... in the app About screen and the repo
     * README"; "The credit and link in the About screen and the README must stay").
     */
    @Test
    fun `the README keeps the credits and links the grants require`() {
        val readme = File("../README.md").readText()
        for (must in listOf(
            "https://github.com/CyanSMP64/NatDexExtension", "CyanSixFour", "CyanSMP64",
            "https://github.com/DrMaple/Faster-FireRed", "https://github.com/DrMaple/Faster-Emerald", "DrMaple",
            "https://github.com/SilverstarStream/faster_black2_white2", "SilverstarStream",
            // IronMON HGSS (2026-10-02), credited as About and NOTICE credit it.
            "https://github.com/PyroMikeGit/IronMONHGSS", "PyroMikeGit", "Foulton",
        )) assertTrue(must in readme, must)
        assertTrue("64-bit" in readme, "the README says a 32-bit phone cannot install it")
    }

    @Test
    fun `the APK carries only the ABIs the emulator cores ship for`() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue("ndk { abiFilters += listOf(\"arm64-v8a\", \"x86_64\") }" in gradle)
        val cores = File("src/main/jniLibs").listFiles()!!.filter { it.isDirectory }.map { it.name }.sorted()
        assertEquals(listOf("arm64-v8a", "x86_64"), cores, "a new core ABI means a new abiFilters entry")
    }
}
