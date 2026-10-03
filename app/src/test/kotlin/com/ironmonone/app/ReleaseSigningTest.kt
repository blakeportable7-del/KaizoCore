package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The release key (rc32 audit P2 #7, #8): a release build is signed with the debug key, the one every installed copy
 * trusts, until Blake's release certificate is pinned in tools/release-certs.txt, and comes out unsigned after it, for
 * tools/sign_release.py to sign with both keys. tools/sign_release_test.py proves the signing with throwaway keys; this
 * pins the build's half, which no JVM test can run, and the shape of the pins file the build and the tools share.
 */
class ReleaseSigningTest {
    private val gradle = File("build.gradle.kts").readText().replace("\r\n", "\n")

    @Test
    fun `a release is signed with the debug key only while no release key is pinned, and unsigned after`() {
        val release = gradle.substringAfter("buildTypes {").substringAfter("release {").substringBefore("\n        }\n")
        assertTrue("signingConfig = if (releaseKeyPinned && !project.hasProperty(\"ironmon.debugSigned\")) null " +
            "else signingConfigs.getByName(\"debug\")" in release, "the release build type's signing")
        assertTrue("signingConfig = signingConfigs.getByName(\"debug\")\n" !in release, "the debug key alone, whatever is pinned")
        val pinned = gradle.substringAfter("val releaseKeyPinned").substringBefore("\n\n")
        assertTrue("rootProject.file(\"tools/release-certs.txt\")" in pinned, "the build reads the pins release.sh and site_bump.py read")
        assertTrue("Regex(\"[0-9a-f]{64}\")" in pinned && "require(it.size == 1)" in pinned, "a malformed pin stops the build")
    }

    @Test
    fun `the pins file names the debug key every installed copy has, and one release line`() {
        val lines = File("../tools/release-certs.txt").readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
        assertEquals(listOf("players c2954523b48a0b59e96e56cdd35cb8048469245f78a50a6e797c0a06a8608b07"), lines.filter { it.startsWith("players") })
        val release = lines.filter { it.split(Regex("\\s+"))[0] == "release" }
        assertEquals(1, release.size, "one release line")
        assertTrue(Regex("release( [0-9a-f]{64})?").matches(release.single()), "empty until Blake pins his key, then its SHA-256")
    }
}
