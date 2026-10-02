package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * rc33 audit P1: the docked facecam never unbound the camera, so it stayed in use (the privacy dot on) after CAM
 * was turned off or Play was left. The release now lives in the preview both camera views share.
 */
class FacecamReleaseTest {
    @Test
    fun `every camera preview lets the camera go when it leaves, and drops a late bind`() {
        val f = File("src/main/kotlin/com/ironmonone/app/Facecam.kt").readText().replace("\r\n", "\n")
        val preview = f.substring(f.indexOf("private fun BoxScope.CameraPreview("))
        assertTrue("onDispose {\n            alive.set(false)" in preview)
        assertTrue("future.get().unbindAll()" in preview)
        assertTrue("if (!alive.get()) return@addListener" in preview, "a bind after the preview went is dropped")
        // Both views draw through it, so neither can keep the camera.
        assertEquals(2, Regex("\\bCameraPreview\\(front, lifecycleOwner\\)").findAll(f).count())
    }
}
