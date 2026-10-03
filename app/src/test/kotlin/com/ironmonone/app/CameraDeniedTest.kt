package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * rc32 audit P2 #21: once Android stops asking for the camera (a second no, or "Don't ask again"), every CAM tap only
 * said "Camera permission denied." and nothing in KaizoCore led back to the setting. A denial for good says where to
 * turn the camera on, and the status line carries a Settings button to KaizoCore's page there.
 */
class CameraDeniedTest {
    @Test
    fun `a denial for good names Settings and offers the button, a first no does not`() {
        val ui = PlayUiState()
        var opened = false
        val line = CameraDenied.note(ui, permanent = true) { opened = true }
        assertEquals(CameraDenied.OFF, line)
        assertTrue("Settings" in line, line)
        val action = ui.toastAction!!
        assertEquals(line, action.first, "the button belongs to this status line (StatusToast matches them)")
        assertEquals(CameraDenied.SETTINGS, action.second)
        action.third()
        assertTrue(opened, "the button opens the app's settings page")

        val again = PlayUiState()
        assertEquals("Camera permission denied.", CameraDenied.note(again, permanent = false) { error("no button for a first no") })
        assertNull(again.toastAction)
        assertFalse(CameraDenied.permanent(null), "with no activity to ask, the denial is not called final")
        for (s in listOf(CameraDenied.OFF, CameraDenied.DENIED, CameraDenied.SETTINGS))
            assertFalse(s.contains(0x2014.toChar()) || s.contains(0x2013.toChar()) || " - " in s, s)
    }

    @Test
    fun `both camera views ask after the no, and Play's two denials go through the note in one line each`() {
        val cam = File("src/main/kotlin/com/ironmonone/app/Facecam.kt").readText()
        assertEquals(2, Regex(Regex.escape("if (!ok) onDenied(CameraDenied.permanent(activity))")).findAll(cam).count(), "FacecamDocked and FacecamBubble")
        assertTrue("!activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)" in cam)
        assertTrue("Settings.ACTION_APPLICATION_DETAILS_SETTINGS" in cam && "Uri.fromParts(\"package\", context.packageName, null)" in cam)
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertEquals(2, Regex(Regex.escape("(onDenied = { permanent -> facecam = false; status = CameraDenied.note(context, ui, permanent) })")).findAll(play).count())
        assertFalse("Camera permission denied." in play)
    }
}
