package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * rc32 audit P3 #14: the facecam is optional, so no camera feature may be required. The CAMERA permission implies
 * android.hardware.camera and its autofocus as required unless the manifest says otherwise, and a store then hides the
 * app from a phone without a rear camera.
 */
class CameraOptionalTest {
    @Test
    fun `every camera feature the permission implies is declared not required`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        for (f in listOf("android.hardware.camera", "android.hardware.camera.autofocus", "android.hardware.camera.any"))
            assertTrue("<uses-feature android:name=\"$f\" android:required=\"false\" />" in manifest, f)
    }
}
