package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc32 audit P2 #59: in landscape CAM drew no camera for a game with no tracker or with the tracker set to Hidden, and
 * with the tracker on a second display the bubble and the docked camera both took the camera, so one went black.
 */
class FacecamPlaceTest {
    private fun place(landscape: Boolean, tracked: Boolean = true, onSecond: Boolean = false, setting: LandscapeTracker = LandscapeTracker.DOCKED,
                      open: Boolean = true, peek: Boolean = false, on: Boolean = true, clean: Boolean = false) =
        FacecamPlace.of(on, clean, landscape, tracked, onSecond, setting, open, peek)

    @Test
    fun `the camera is drawn in exactly one place`() {
        assertEquals(FacecamPlace.BUBBLE, place(landscape = true, tracked = false), "a game with no tracker")
        assertEquals(FacecamPlace.BUBBLE, place(landscape = true, setting = LandscapeTracker.HIDDEN), "the tracker set to Hidden")
        assertEquals(FacecamPlace.DOCKED, place(landscape = true, setting = LandscapeTracker.HIDDEN, peek = true), "shown for now")
        assertEquals(FacecamPlace.DOCKED, place(landscape = true), "docked beside the game")
        assertEquals(FacecamPlace.BUBBLE, place(landscape = true, open = false), "the docked tracker hidden")
        assertEquals(FacecamPlace.DOCKED, place(landscape = true, setting = LandscapeTracker.FLOATING, open = false), "the window draws whatever Play thinks")
        assertEquals(FacecamPlace.BUBBLE, place(landscape = false), "portrait has no column to dock into")
        assertEquals(FacecamPlace.DOCKED, place(landscape = false, onSecond = true), "with the tracker on a second display, only there")
        assertEquals(FacecamPlace.DOCKED, place(landscape = true, onSecond = true))
        assertEquals(FacecamPlace.NONE, place(landscape = true, on = false))
        assertEquals(FacecamPlace.NONE, place(landscape = false, clean = true), "clean view stays clean")
    }

    @Test
    fun `Play draws the bubble only where the place says, and CAM says when it is on`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        assertTrue("if (FacecamPlace.of(facecam, streamClean, landscape, session.tracked, trackerOnSecond, TrackerOptions.landscapeTracker, trackerOpen, ui.trackerPeek) == FacecamPlace.BUBBLE) {" in play)
        assertFalse("if (facecam && !streamClean && !(landscape && trackerOpen))" in play)
        assertTrue("facecam = facecam, onCam = { facecam = !facecam }," in play, "the FILE bar's Camera switch (FileBar.kt)")
    }
}
