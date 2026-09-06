package com.ironmonone.app

import com.ironmonone.app.engine.ZxEngine
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Settings-string import, against a REAL string from the official page.
 *
 * The import used to hand the whole string to Settings.fromString, which
 * expects bare base64. Every real string starts with its three-digit
 * version, so every real string failed as "Malformed input" - the feature
 * had never worked on the input it exists for.
 */
class SettingsStringTest {

    private fun officialString(tag: String, mode: String): String =
        File("../tools/upr-settings/strings.tsv").readLines()
            .filter { !it.startsWith("#") && it.isNotBlank() }
            .map { it.split('\t') }
            .first { it[0] == tag && it[1] == mode }[3]

    @Test
    fun `a real string from the page is accepted`() {
        assertNull(ZxEngine.validateSettingsString(officialString("HGSS", "Kaizo")))
        // The one string on the page already at this app's version.
        assertNull(ZxEngine.validateSettingsString(officialString("B2W2", "Kaizo Doubles")))
    }

    @Test
    fun `an older string is updated, not refused`() {
        // GSC strings are version 321; the app carries 322.
        val s = officialString("GSC", "Survival")
        assertTrue(s.startsWith("321"))
        assertNull(ZxEngine.validateSettingsString(s))
    }

    @Test
    fun `a newer string and garbage are refused with a reason`() {
        val newer = "999" + officialString("HGSS", "Kaizo").substring(3)
        val msg = assertNotNull(ZxEngine.validateSettingsString(newer))
        assertTrue("newer" in msg, msg)
        assertNotNull(ZxEngine.validateSettingsString("not a settings string"))
        assertNotNull(ZxEngine.validateSettingsString(""))
    }
}
